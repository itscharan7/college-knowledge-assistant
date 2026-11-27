package com.collegeassistant.document;

import com.collegeassistant.user.Role;
import java.util.List;

/**
 * Single source of truth for "which audiences can this role see". Used by retrieval (as a vector
 * metadata filter BEFORE the LLM sees anything), by the PDF file endpoint, and by document listing.
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static List<Audience> allowedAudiences(Role role) {
        return switch (role) {
            case STUDENT -> List.of(Audience.ALL, Audience.STUDENT);
            case FACULTY -> List.of(Audience.ALL, Audience.STUDENT, Audience.FACULTY);
            case ADMIN -> List.of(Audience.ALL, Audience.STUDENT, Audience.FACULTY, Audience.ADMIN);
        };
    }

    public static boolean canView(Role role, Audience audience) {
        return allowedAudiences(role).contains(audience);
    }
}
