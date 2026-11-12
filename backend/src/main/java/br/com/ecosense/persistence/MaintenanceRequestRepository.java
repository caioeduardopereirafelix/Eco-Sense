package br.com.ecosense.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequestEntity, Long> {

    List<MaintenanceRequestEntity> findAllByOrderByIdAsc();

    List<MaintenanceRequestEntity> findAllByRequestedByIdOrderByIdAsc(Long requestedById);
}
