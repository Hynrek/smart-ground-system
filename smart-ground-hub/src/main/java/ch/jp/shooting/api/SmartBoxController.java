package ch.jp.shooting.api;

import ch.jp.smartground.api.SmartBoxApi;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.SetAliasRequest;
import ch.jp.smartground.model.SmartBoxPageResponse;
import ch.jp.smartground.model.SmartBoxResponse;
import ch.jp.smartground.model.SmartBoxStatus;
import ch.jp.shooting.config.SmartBoxConfigPushService;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.SmartBoxRepository;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@NullMarked
public class SmartBoxController implements SmartBoxApi {

    private final SmartBoxRepository smartBoxRepository;
    private final SmartBoxConfigPushService configPushService;

    public SmartBoxController(SmartBoxRepository smartBoxRepository,
                               SmartBoxConfigPushService configPushService) {
        this.smartBoxRepository = smartBoxRepository;
        this.configPushService = configPushService;
    }

    @Override
    public ResponseEntity<SmartBoxPageResponse> listSmartBoxes(
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "50") Integer size) {

        Page<SmartBox> boxPage = smartBoxRepository.findAll(PageRequest.of(page, size));

        SmartBoxPageResponse response = new SmartBoxPageResponse();
        response.setContent(boxPage.getContent().stream().map(this::toResponse).toList());
        response.setMeta(new PageMeta()
            .page(boxPage.getNumber())
            .size(boxPage.getSize())
            .totalElements((int) boxPage.getTotalElements())
            .totalPages(boxPage.getTotalPages()));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SmartBoxResponse> getSmartBox(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(toResponse(findBox(id)));
    }

    @Override
    public ResponseEntity<SmartBoxResponse> setSmartBoxAlias(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SetAliasRequest request) {

        SmartBox box = findBox(id);
        box.setAlias(request.getAlias());
        return ResponseEntity.ok(toResponse(smartBoxRepository.save(box)));
    }

    @Override
    public ResponseEntity<Void> pushSmartBoxConfig(@PathVariable("id") UUID id) {
        SmartBox box = findBox(id);
        box.setConfigSynced(false);
        configPushService.push(smartBoxRepository.save(box));
        return ResponseEntity.accepted().build();
    }

    private SmartBox findBox(UUID id) {
        return smartBoxRepository.findById(id)
            .orElseThrow(() -> new SmartBoxNotFoundException(id));
    }

    private SmartBoxResponse toResponse(SmartBox box) {
        SmartBoxStatus statusEnum = SmartBoxStatus.fromValue(box.getStatus().name());
        return new SmartBoxResponse()
            .id(box.getId())
            .macAddress(box.getMacAddress())
            .alias(box.getAlias())
            .status(statusEnum)
            .appVersion(box.getAppVersion())
            .firmwareVersion(box.getFirmwareVersion())
            .configSynced(box.isConfigSynced());
    }
}