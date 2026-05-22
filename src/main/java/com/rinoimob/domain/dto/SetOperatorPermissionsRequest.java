package com.rinoimob.domain.dto;

import java.util.List;

public record SetOperatorPermissionsRequest(
    List<String> permissions
) {}
