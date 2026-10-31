package com.campusclaw.health;

import static org.assertj.core.api.Assertions.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

class DeploymentVersionTest {
    private DeploymentVersion version(String override) {
        var env = new MockEnvironment();
        if (override != null) env.setProperty("APP_VERSION", override);
        var props = new Properties();
        props.setProperty("version", "0.1.0");
        var beans = new StaticListableBeanFactory();
        beans.addBean("build", new BuildProperties(props));
        return new DeploymentVersion(env, beans.getBeanProvider(BuildProperties.class));
    }

    @Test void usesPackagedVersionOrValidatedOverride() {
        assertThat(version(null).value()).isEqualTo("0.1.0");
        assertThat(version("release_07-1.2").value()).isEqualTo("release_07-1.2");
        assertThat(version("a".repeat(64)).value()).hasSize(64);
    }

    @Test void rejectsEmptyLongAndUnsafeWithoutEchoingValue() {
        for (String bad : new String[]{"", "a".repeat(65), "secret/unsafe", "line\nbreak"}) {
            assertThatThrownBy(() -> version(bad)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("APP_VERSION or build version metadata is invalid");
        }
    }
}
