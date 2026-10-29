package com.shortener.orchestration.agents;

import com.shortener.orchestration.context.PipelineContextService;
import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;

import java.nio.file.Path;

public record AgentContext(
        PipelineRun run,
        StageExecution stage,
        PipelineContextService context,
        PipelineStore store,
        Path runWorkspace) {

    public Path stageWorkspace() {
        return runWorkspace.resolve(stage.getStageName());
    }
}
