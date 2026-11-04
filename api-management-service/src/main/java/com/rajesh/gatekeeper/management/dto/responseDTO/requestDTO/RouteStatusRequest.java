package com.rajesh.gatekeeper.management.dto.responseDTO.requestDTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteStatusRequest {

    @NotNull
    private Boolean active;
}
