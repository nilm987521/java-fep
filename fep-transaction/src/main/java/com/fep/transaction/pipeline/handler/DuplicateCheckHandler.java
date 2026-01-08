package com.fep.transaction.pipeline.handler;

import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import com.fep.transaction.validator.DuplicateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline handler for duplicate transaction checking.
 */
public class DuplicateCheckHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(DuplicateCheckHandler.class);

    private final DuplicateChecker duplicateChecker;

    public DuplicateCheckHandler(DuplicateChecker duplicateChecker) {
        this.duplicateChecker = duplicateChecker;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.DUPLICATE_CHECK;
    }

    @Override
    public void handle(PipelineContext context) {
        log.debug("[{}] Checking for duplicate transaction", context.getPipelineId());

        duplicateChecker.validate(context.getRequest());

        log.debug("[{}] Duplicate check passed", context.getPipelineId());
    }
}
