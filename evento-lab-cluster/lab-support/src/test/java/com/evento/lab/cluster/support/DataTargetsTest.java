package com.evento.lab.cluster.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTargetsTest {

    @Test
    void multiHostUrlsKeepTheirCommas() {
        var t = DataTargets.parse("EU=jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary,"
                + " am=jdbc:postgresql://toxi-eu:20122/app ; apac=jdbc:postgresql://toxi-eu:20132/app");
        assertThat(t.keySet()).containsExactly("eu", "am", "apac");
        assertThat(t.get("eu")).isEqualTo("jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary");
        assertThat(t.get("am")).isEqualTo("jdbc:postgresql://toxi-eu:20122/app");
        assertThat(t.get("apac")).isEqualTo("jdbc:postgresql://toxi-eu:20132/app");
    }

    @Test
    void emptyMeansNoTargets() {
        assertThat(DataTargets.parse(null)).isEmpty();
        assertThat(DataTargets.parse("  ")).isEmpty();
    }

    @Test
    void rejectsMalformedEntries() {
        assertThatThrownBy(() -> DataTargets.parse("jdbc:postgresql://x/app")).hasMessageContaining("name=jdbc");
        assertThatThrownBy(() -> DataTargets.parse("eu=postgres://x/app")).hasMessageContaining("no JDBC URL");
        assertThatThrownBy(() -> DataTargets.parse("eu=jdbc:a;EU=jdbc:b")).hasMessageContaining("twice");
        assertThatThrownBy(() -> DataTargets.parse("e u=jdbc:a")).hasMessageContaining("bad target name");
    }
}
