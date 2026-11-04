package com.rajesh.gatekeeper.management.repo;

import com.rajesh.gatekeeper.management.entity.ApiRoute;
import com.rajesh.gatekeeper.management.entity.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiRouteRepository extends JpaRepository<ApiRoute, Long> {

    List<ApiRoute> findAllByOrganizationId(Long organizationId);

    List<ApiRoute> findAllByActiveTrueAndOrganizationStatus(OrganizationStatus status);

    Optional<ApiRoute> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndRouteId(Long organizationId, String routeId);
}