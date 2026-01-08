package com.fep.transaction.pipeline.handler;

import com.fep.transaction.logging.TransactionLogger;
import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline handler for audit logging.
 */
public class AuditHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditHandler.class);

    private final TransactionLogger transactionLogger;

    public AuditHandler(TransactionLogger transactionLogger) {
        this.transactionLogger = transactionLogger;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.AUDIT;
    }

    @Override
    public void handle(PipelineContext context) {
        log.debug("[{}] Recording audit log", context.getPipelineId());

        // Log the request
        transactionLogger.logRequest(context.getRequest());

        // Log the response if available
        if (context.getResponse() != null) {
            transactionLogger.logResponse(context.getRequest(), context.getResponse());
        }

        // Log timing information
        log.info(context.getTimingSummary());
    }
}
