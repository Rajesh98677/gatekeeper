package com.rajesh.gatekeeper.management.service;

import com.rajesh.gatekeeper.management.entity.Organization;
import com.rajesh.gatekeeper.management.entity.OrganizationStatus;
import com.rajesh.gatekeeper.management.exception.ApiException;
import com.rajesh.gatekeeper.management.repo.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    private final OrganizationRepository organizationRepository;

    public TenantContext(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization getOrganization() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(!(authentication instanceof JwtAuthenticationToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tenant identity is missing");
        }

        Long organizationId = ((JwtAuthenticationToken) authentication).getToken()
                .getClaim("organizationId");

        if(organizationId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tenant identity is missing");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Organization does not exist"));

        if(organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The organization is not active");
        }

        return organization;
    }
}