package com.shortener.orchestration.gates;

import com.shortener.orchestration.context.PipelineContextService;
import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.dag.StageDefinition;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;

import java.nio.file.Path;

/** Everything a gate needs to evaluate: run, stage, artifact context, and workspace. */
public record GateContext(
        PipelineRun run,
        StageDefinition stageDefinition,
        StageExecution stageExecution,
        PipelineContextService context,
        PipelineStore store,
        Path runWorkspace) {

    public Path stageWorkspace() {
        return runWorkspace.resolve(stageDefinition.name());
    }
}
