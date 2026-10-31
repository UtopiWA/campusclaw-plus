package com.campusclaw.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DeploymentVersion {
    private final String value;

    public DeploymentVersion(Environment environment, ObjectProvider<BuildProperties> builds) {
        String configured = environment.getProperty("APP_VERSION");
        BuildProperties build = builds.getIfAvailable();
        String version = configured != null ? configured : build == null ? null : build.getVersion();
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            // 配置错误只报告键名，不把未经校验的配置值写入启动日志。
            throw new IllegalStateException("APP_VERSION or build version metadata is invalid");
        }
        this.value = version;
    }

    public String value() {
        return value;
    }
}
