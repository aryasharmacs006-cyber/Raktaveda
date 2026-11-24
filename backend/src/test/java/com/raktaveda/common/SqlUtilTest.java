package com.raktaveda.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SqlUtilTest {

    @Test
    void buildsPlaceholders() {
        assertThat(SqlUtil.placeholders(1)).isEqualTo("?");
        assertThat(SqlUtil.placeholders(3)).isEqualTo("?,?,?");
    }

    @Test
    void rejectsZero() {
        assertThatThrownBy(() -> SqlUtil.placeholders(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
