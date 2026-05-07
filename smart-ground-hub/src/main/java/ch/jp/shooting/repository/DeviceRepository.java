package ch.jp.shooting.repository;

import ch.jp.shooting.model.Device;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findBySmartBoxId(UUID smartBoxId);
    Page<Device> findBySmartBoxId(UUID smartBoxId, Pageable pageable);
    Page<Device> findByRangeId(UUID rangeId, Pageable pageable);
}