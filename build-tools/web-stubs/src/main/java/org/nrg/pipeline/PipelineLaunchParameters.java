package org.nrg.pipeline;

import org.nrg.xft.security.UserI;

/** Compilation stub for circular dependency resolution. */
public class PipelineLaunchParameters {

    public static PipelineLaunchParametersBuilder builder() { return new PipelineLaunchParametersBuilder(); }

    public void setPipelineName(String name) {}
    public void setStartAt(String startAt) {}
    public void setDataType(String dataType) {}
    public void setId(String id) {}
    public void setSupressNotification(Boolean suppress) {}
    public void setParameter(String name, String value) {}
    public void setParameterFile(String path) {}
    public void notificationEmailId(String emailId) {}

    public static class PipelineLaunchParametersBuilder {
        public PipelineLaunchParametersBuilder user(UserI user) { return this; }
        public PipelineLaunchParameters build() { return new PipelineLaunchParameters(); }
    }
}
