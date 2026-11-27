package com.collegeassistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.collegeassistant.user.Role;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

    @Test
    void studentSeesOnlyAllAndStudent() {
        assertThat(AccessPolicy.allowedAudiences(Role.STUDENT)).containsExactly(Audience.ALL, Audience.STUDENT);
        assertThat(AccessPolicy.canView(Role.STUDENT, Audience.FACULTY)).isFalse();
        assertThat(AccessPolicy.canView(Role.STUDENT, Audience.ADMIN)).isFalse();
    }

    @Test
    void facultySeesFacultyButNotAdminOnly() {
        assertThat(AccessPolicy.canView(Role.FACULTY, Audience.FACULTY)).isTrue();
        assertThat(AccessPolicy.canView(Role.FACULTY, Audience.ADMIN)).isFalse();
    }

    @Test
    void adminSeesEverything() {
        for (Audience a : Audience.values()) {
            assertThat(AccessPolicy.canView(Role.ADMIN, a)).isTrue();
        }
    }
}
