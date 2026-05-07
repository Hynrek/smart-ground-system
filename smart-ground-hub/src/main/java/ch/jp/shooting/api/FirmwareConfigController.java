package ch.jp.shooting.api;

import ch.jp.shooting.dto.FirmwareConfigManifest;
import ch.jp.shooting.model.FirmwareConfig;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.service.FirmwareConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/device-types/firmware-configs")
@NullMarked
@Tag(name = "DeviceType", description = "Firmware configurations and signal type definitions")
public class FirmwareConfigController {

    private final FirmwareConfigService firmwareConfigService;
    private final FirmwareConfigRepository firmwareConfigRepository;

    public FirmwareConfigController(FirmwareConfigService firmwareConfigService,
                                     FirmwareConfigRepository firmwareConfigRepository) {
        this.firmwareConfigService = firmwareConfigService;
        this.firmwareConfigRepository = firmwareConfigRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new firmware configuration",
        description = "Creates a new firmware configuration with associated signal types. Requires ADMIN role.")
    @ApiResponse(responseCode = "201", description = "Firmware configuration created successfully",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = FirmwareConfigResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid manifest or duplicate firmware version")
    @ApiResponse(responseCode = "403", description = "User does not have ADMIN role")
    public ResponseEntity<FirmwareConfigResponse> registerFirmwareConfig(
            @Valid @RequestBody FirmwareConfigManifest manifest) {
        FirmwareConfig fc = firmwareConfigService.register(manifest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(fc));
    }

    @GetMapping
    @Operation(summary = "List all firmware configurations", description = "Returns a list of all registered firmware configurations")
    @ApiResponse(responseCode = "200", description = "List of firmware configurations",
        content = @Content(mediaType = "application/json"))
    public ResponseEntity<List<FirmwareConfigResponse>> listFirmwareConfigs() {
        List<FirmwareConfig> configs = firmwareConfigRepository.findAll();
        return ResponseEntity.ok(configs.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get firmware configuration by ID", description = "Retrieves a specific firmware configuration by its UUID")
    @ApiResponse(responseCode = "200", description = "Firmware configuration found",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = FirmwareConfigResponse.class)))
    @ApiResponse(responseCode = "404", description = "Firmware configuration not found")
    public ResponseEntity<FirmwareConfigResponse> getFirmwareConfig(@PathVariable UUID id) {
        return firmwareConfigRepository.findById(id)
            .map(fc -> ResponseEntity.ok(toResponse(fc)))
            .orElse(ResponseEntity.notFound().build());
    }

    private FirmwareConfigResponse toResponse(FirmwareConfig fc) {
        return new FirmwareConfigResponse(
            fc.getId(),
            fc.getVersion(),
            fc.getBoxType(),
            fc.getSignalTypes().stream()
                .map(st -> new FirmwareConfigResponse.SignalTypeResponse(
                    st.getId(),
                    st.getCommunicationDirection().name(),
                    st.getDevice().name(),
                    st.getCommand()
                ))
                .toList()
        );
    }

    public record FirmwareConfigResponse(
        UUID id,
        String version,
        String boxType,
        List<SignalTypeResponse> signalTypes
    ) {
        public record SignalTypeResponse(
            UUID id,
            String communicationDirection,
            String device,
            String command
        ) {}
    }
}
