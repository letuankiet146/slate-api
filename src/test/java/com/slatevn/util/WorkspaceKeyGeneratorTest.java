package com.slatevn.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkspaceKeyGeneratorTest {

    @Test
    void slugFromVietnameseName() {
        assertEquals("NHOMLAMVIECTAINHA", WorkspaceKeyGenerator.slugFromName("nhóm làm việc tại nhà"));
    }

    @Test
    void slugFromNameWithD() {
        assertEquals("DOINGU", WorkspaceKeyGenerator.slugFromName("đội ngũ"));
    }

    @Test
    void generateUniqueKeyAppendsSuffixWhenTaken() {
        String key = WorkspaceKeyGenerator.generateUniqueKey(
                "nhóm làm việc tại nhà",
                candidate -> "NHOMLAMVIECTAINHA".equals(candidate)
        );
        assertEquals("NHOMLAMVIECTAINHA1", key);
    }

    @Test
    void isValidKeyRejectsBlank() {
        assertFalse(WorkspaceKeyGenerator.isValidKey(""));
        assertFalse(WorkspaceKeyGenerator.isValidKey("a"));
    }
}
