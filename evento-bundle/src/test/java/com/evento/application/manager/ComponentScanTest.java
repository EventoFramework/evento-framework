package com.evento.application.manager;

import com.evento.application.manager.scanfixtures.ScannedService;
import com.evento.application.manager.scanfixtures.sub.ExtendedService;
import com.evento.application.manager.scanfixtures.sub.PlainClass;
import com.evento.common.modeling.annotations.component.Component;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.component.Service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the scanner a bundle finds its components with: a scanner upgrade that silently finds
 * nothing (ClassGraph 6 scans no source unless told to) must fail here, not as "no handler" in
 * every integration test.
 */
class ComponentScanTest {

    private static final String PACKAGE = "com.evento.application.manager.scanfixtures";

    @Test
    void findsAnnotatedClassesAndTheirSubclassesInSubPackages() {
        var scan = ComponentScan.of(PACKAGE);

        assertThat(scan.annotatedWith(Service.class))
                .containsExactlyInAnyOrder(ScannedService.class, ExtendedService.class);
        assertThat(scan.allClasses())
                .contains(ScannedService.class, ExtendedService.class, PlainClass.class);
    }

    @Test
    void matchesOnlyTheAnnotationOnTheClassNotItsMetaAnnotations() {
        var scan = ComponentScan.of(PACKAGE);

        assertThat(scan.annotatedWith(Component.class)).isEmpty();
        assertThat(scan.annotatedWith(Projector.class)).isEmpty();
    }

    @Test
    void neverReachesOutsideTheBasePackage() {
        var scan = ComponentScan.of(PACKAGE + ".sub");

        assertThat(scan.allClasses()).containsExactlyInAnyOrder(ExtendedService.class, PlainClass.class);
        assertThat(scan.annotatedWith(Service.class)).isEmpty();
    }
}
