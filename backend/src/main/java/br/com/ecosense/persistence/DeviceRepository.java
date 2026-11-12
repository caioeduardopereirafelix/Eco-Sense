package br.com.ecosense.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {

    List<DeviceEntity> findAllByOrderByPositionAscIdAsc();

    @Query("select coalesce(max(d.position), 0) from DeviceEntity d")
    int maxPosition();
}
