package ch.jp.shooting.service;

import ch.jp.shooting.config.MqttCommandPublisher;
import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.DeviceNotFoundException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.RangePositionNotFoundException;
import ch.jp.shooting.exception.RangePositionOccupiedException;
import ch.jp.shooting.mapper.EntityMappers;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.smartground.model.AssignDeviceToPositionRequest;
import ch.jp.smartground.model.CommandResponse;
import ch.jp.smartground.model.CreateRangePositionRequest;
import ch.jp.smartground.model.RangePositionResponse;
import ch.jp.smartground.model.UpdateRangePositionLabelRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Verwaltung von Positionen (Buchstaben-Slots) innerhalb eines Schiessplatzes. */
@Service
@NullMarked
public class RangePositionService {

    private static final Pattern AUTO_LABEL_PATTERN = Pattern.compile("^[A-Z]+$");

    private final RangeRepository rangeRepository;
    private final RangePositionRepository positionRepository;
    private final DeviceRepository deviceRepository;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final ReservationService reservationService;

    public RangePositionService(RangeRepository rangeRepository,
                                RangePositionRepository positionRepository,
                                DeviceRepository deviceRepository,
                                MqttCommandPublisher mqttCommandPublisher,
                                ReservationService reservationService) {
        this.rangeRepository = rangeRepository;
        this.positionRepository = positionRepository;
        this.deviceRepository = deviceRepository;
        this.mqttCommandPublisher = mqttCommandPublisher;
        this.reservationService = reservationService;
    }

    // ── Abfragen ──────────────────────────────────────────────────────────────

    public List<RangePositionResponse> listPositions(UUID rangeId) {
        ladeRange(rangeId); // prüft Existenz
        return positionRepository.findByRangeIdOrderBySortOrderAsc(rangeId).stream()
            .map(EntityMappers::toRangePositionResponse)
            .toList();
    }

    // ── Erstellen ─────────────────────────────────────────────────────────────

    @Transactional
    public RangePositionResponse createPosition(UUID rangeId, @Nullable String labelOpt) {
        var range = ladeRange(rangeId);
        pruefeNichtGesperrt(range);

        String label = labelOpt != null && !labelOpt.isBlank()
            ? labelOpt.trim()
            : naechsterAutoLabel(rangeId);

        pruefeEindeutigesBeschriftung(rangeId, label, null);

        int sortOrder = positionRepository.countByRangeId(rangeId);
        var position = new RangePosition();
        position.setRange(range);
        position.setLabel(label);
        position.setSortOrder(sortOrder);

        return EntityMappers.toRangePositionResponse(positionRepository.save(position));
    }

    // ── Umbenennen ────────────────────────────────────────────────────────────

    @Transactional
    public RangePositionResponse renamePosition(UUID rangeId, UUID positionId, String newLabel) {
        var range = ladeRange(rangeId);
        pruefeNichtGesperrt(range);
        var position = ladePosition(positionId, rangeId);

        pruefeEindeutigesBeschriftung(rangeId, newLabel.trim(), positionId);
        position.setLabel(newLabel.trim());

        return EntityMappers.toRangePositionResponse(positionRepository.save(position));
    }

    // ── Löschen ───────────────────────────────────────────────────────────────

    @Transactional
    public void deletePosition(UUID rangeId, UUID positionId) {
        var range = ladeRange(rangeId);
        pruefeNichtGesperrt(range);
        var position = ladePosition(positionId, rangeId);

        if (position.getDevice() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Position " + position.getLabel() + " hat noch ein Gerät. Bitte zuerst entfernen.");
        }
        positionRepository.delete(position);
    }

    // ── Gerät zuordnen ────────────────────────────────────────────────────────

    @Transactional
    public RangePositionResponse assignDevice(UUID rangeId, UUID positionId, UUID deviceId) {
        var range = ladeRange(rangeId);
        pruefeNichtGesperrt(range);
        var position = ladePosition(positionId, rangeId);
        var device = ladeDevice(deviceId);

        // Gerät darf nicht bereits in einer anderen Position sein
        if (device.getRangePosition() != null
            && !device.getRangePosition().getId().equals(positionId)) {
            throw new RangePositionOccupiedException(deviceId);
        }

        // Wenn Position bereits ein anderes Gerät hat, dieses freigeben
        var altesBelegt = position.getDevice();
        if (altesBelegt != null && !altesBelegt.getId().equals(deviceId)) {
            altesBelegt.setRangePosition(null);
            altesBelegt.setRange(null);
            deviceRepository.save(altesBelegt);
        }

        position.setDevice(device);
        device.setRangePosition(position);
        device.setRange(range); // range_id in sync halten

        deviceRepository.save(device);
        return EntityMappers.toRangePositionResponse(positionRepository.save(position));
    }

    // ── Gerät entfernen ───────────────────────────────────────────────────────

    @Transactional
    public RangePositionResponse removeDevice(UUID rangeId, UUID positionId) {
        var range = ladeRange(rangeId);
        pruefeNichtGesperrt(range);
        var position = ladePosition(positionId, rangeId);

        var device = position.getDevice();
        if (device != null) {
            device.setRangePosition(null);
            device.setRange(null);
            deviceRepository.save(device);
        }
        position.setDevice(null);
        return EntityMappers.toRangePositionResponse(positionRepository.save(position));
    }

    // ── Befehl senden ─────────────────────────────────────────────────────────

    /** Löst die Position auf, prüft Berechtigungen und sendet den MQTT-Befehl an das Gerät. */
    public CommandResponse sendPositionCommand(UUID rangeId, UUID positionId, String username, boolean isAdmin) {
        var position = ladePosition(positionId, rangeId);

        var device = position.getDevice();
        if (device == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Position " + position.getLabel() + " hat kein Gerät zugeordnet.");
        }
        if (device.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gerät ist gesperrt");
        }

        var range = position.getRange();
        if (!reservationService.canUserCommandRange(range.getId(), username, isAdmin)) {
            throw new ConflictException("Already reserved");
        }
        var reservation = reservationService.getActiveReservation(range.getId());
        if (reservation != null) {
            reservationService.markActivity(reservation.getId());
        }

        String command = device.getDeviceType().getSignalType().getCommand();
        String mac = device.getSmartBox().getMacAddress();
        String topic = "smartboxes/" + mac + "/command";
        int signalDurationMs = device.getDeviceType().getSignalDurationMs();
        Integer delaySignalDurationMs = device.getDelaySignalDurationMs() != null
            ? device.getDelaySignalDurationMs()
            : device.getDeviceType().getDelaySignalDurationMs();

        mqttCommandPublisher.publishToTopic(topic, command, device.getId().toString(), signalDurationMs, delaySignalDurationMs);

        return new CommandResponse().status("accepted");
    }

    // ── Play-Logik: posId → Device ────────────────────────────────────────────

    /** Löst einen posId-Label-String zur Geräte-UUID auf. Gibt null zurück wenn Slot leer. */
    public @Nullable UUID resolveDeviceForPosition(UUID rangeId, String posId) {
        return positionRepository.findByRangeIdAndLabel(rangeId, posId)
            .map(RangePosition::getDevice)
            .map(Device::getId)
            .orElse(null);
    }

    // ── Private Hilfsmethoden ─────────────────────────────────────────────────

    private Range ladeRange(UUID rangeId) {
        return rangeRepository.findById(rangeId)
            .orElseThrow(() -> new RangeNotFoundException(rangeId));
    }

    private RangePosition ladePosition(UUID positionId, UUID rangeId) {
        var position = positionRepository.findById(positionId)
            .orElseThrow(() -> new RangePositionNotFoundException(positionId));
        if (!position.getRange().getId().equals(rangeId)) {
            throw new RangePositionNotFoundException(positionId);
        }
        return position;
    }

    private Device ladeDevice(UUID deviceId) {
        return deviceRepository.findById(deviceId)
            .orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }

    private void pruefeNichtGesperrt(Range range) {
        if (range.isLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "Schiessplatz '" + range.getName() + "' ist gesperrt.");
        }
    }

    private void pruefeEindeutigesBeschriftung(UUID rangeId, String label,
                                                @Nullable UUID excludePositionId) {
        positionRepository.findByRangeIdAndLabel(rangeId, label).ifPresent(existing -> {
            if (!existing.getId().equals(excludePositionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Beschriftung '" + label + "' existiert bereits auf diesem Platz.");
            }
        });
    }

    /**
     * Berechnet den nächsten automatischen Buchstaben (A, B, … Z, AA, AB …).
     * Nur Positionen mit reinen Grossbuchstaben werden berücksichtigt.
     */
    private String naechsterAutoLabel(UUID rangeId) {
        var vorhandene = positionRepository.findByRangeIdOrderBySortOrderAsc(rangeId).stream()
            .map(RangePosition::getLabel)
            .filter(l -> AUTO_LABEL_PATTERN.matcher(l).matches())
            .collect(java.util.stream.Collectors.toSet());

        // A–Z, dann AA–ZZ usw.
        for (int laenge = 1; laenge <= 3; laenge++) {
            var kandidat = ersteBuchstabenKombination(laenge, vorhandene);
            if (kandidat != null) return kandidat;
        }
        return "POS-" + (positionRepository.countByRangeId(rangeId) + 1);
    }

    private @Nullable String ersteBuchstabenKombination(int laenge, java.util.Set<String> vorhandene) {
        return generiereLabels(laenge).stream()
            .filter(l -> !vorhandene.contains(l))
            .findFirst()
            .orElse(null);
    }

    private List<String> generiereLabels(int laenge) {
        if (laenge == 1) {
            return java.util.stream.IntStream.rangeClosed('A', 'Z')
                .mapToObj(c -> String.valueOf((char) c))
                .toList();
        }
        // Rekursiv für Länge > 1 (AA–ZZ etc.)
        var result = new java.util.ArrayList<String>();
        for (char prefix = 'A'; prefix <= 'Z'; prefix++) {
            for (var suffix : generiereLabels(laenge - 1)) {
                result.add(prefix + suffix);
            }
        }
        return result;
    }
}
