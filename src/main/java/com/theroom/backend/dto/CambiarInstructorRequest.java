package com.theroom.backend.dto;

import lombok.Data;

@Data
public class CambiarInstructorRequest {
    // null = desasignar instructor
    private Long instructorId;
}
