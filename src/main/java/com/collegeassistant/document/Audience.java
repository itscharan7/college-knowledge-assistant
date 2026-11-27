package com.collegeassistant.document;

/** Who may see a document. ALL and STUDENT are visible to everyone; FACULTY to faculty+admin; ADMIN to admin only. */
public enum Audience {
    ALL,
    STUDENT,
    FACULTY,
    ADMIN
}
