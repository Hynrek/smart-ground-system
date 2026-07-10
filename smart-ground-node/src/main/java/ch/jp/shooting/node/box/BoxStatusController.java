package ch.jp.shooting.node.box;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
public class BoxStatusController {

    private final BoxRecordRepository repository;

    public BoxStatusController(BoxRecordRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/box-api/v1/boxes/{macAddress}/status")
    public void status(@PathVariable String macAddress, @RequestBody BoxStatusRequest request) {
        BoxRecord record = repository.findByMacAddress(macAddress).orElseThrow(() -> {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Box " + macAddress + " ist nicht provisioniert.");
            detail.setType(URI.create("/errors/box-unknown"));
            return new ErrorResponseException(HttpStatus.NOT_FOUND, detail, null);
        });
        record.setLastStatus(request.status());
        record.setLastSeenAt(Instant.now());
        repository.save(record);
    }
}
