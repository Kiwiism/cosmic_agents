package server.agents.capabilities.build;

/**
 * Mutable runtime state for AP/SP build and job-advancement prompting.
 */
public final class AgentBuildState {
    private AgentBuildService.ApBuild apBuild = null;
    private boolean apPromptSent = false;
    private String spVariant = null;
    private boolean spVariantPromptSent = false;
    private int jobPromptSent = 0;
    private int lastKnownLevel = -1;

    public AgentBuildService.ApBuild apBuild() {
        return apBuild;
    }

    public boolean hasApBuild() {
        return apBuild != null;
    }

    public void setApBuild(AgentBuildService.ApBuild apBuild) {
        this.apBuild = apBuild;
        this.apPromptSent = false;
    }

    public void clearApBuildPromptState() {
        apBuild = null;
        apPromptSent = false;
    }

    public boolean apPromptSent() {
        return apPromptSent;
    }

    public void markApPromptSent() {
        this.apPromptSent = true;
    }

    public String spVariant() {
        return spVariant;
    }

    public boolean hasSpVariant() {
        return spVariant != null;
    }

    public void setSpVariant(String spVariant) {
        this.spVariant = spVariant;
    }

    public boolean spVariantPromptSent() {
        return spVariantPromptSent;
    }

    public void markSpVariantPromptSent() {
        this.spVariantPromptSent = true;
    }

    public int jobPromptSent() {
        return jobPromptSent;
    }

    public void setJobPromptSent(int jobPromptSent) {
        this.jobPromptSent = jobPromptSent;
    }

    public int lastKnownLevel() {
        return lastKnownLevel;
    }

    public void setLastKnownLevel(int lastKnownLevel) {
        this.lastKnownLevel = lastKnownLevel;
    }
}
