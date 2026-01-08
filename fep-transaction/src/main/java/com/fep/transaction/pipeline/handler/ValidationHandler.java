package com.fep.transaction.pipeline.handler;

import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import com.fep.transaction.validator.TransactionValidator;
import com.fep.transaction.validator.ValidationChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Pipeline handler for business validation.
 */
public class ValidationHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidationHandler.class);

    private final ValidationChain validationChain;

    public ValidationHandler(ValidationChain validationChain) {
        this.validationChain = validationChain;
    }

    public ValidationHandler(List<TransactionValidator> validators) {
        this.validationChain = new ValidationChain(validators);
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.VALIDATION;
    }

    @Override
    public void handle(PipelineContext context) {
        log.debug("[{}] Running business validations", context.getPipelineId());

        validationChain.validate(context.getRequest());

        log.debug("[{}] All validations passed", context.getPipelineId());
    }
}
