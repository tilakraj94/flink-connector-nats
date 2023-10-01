// Copyright (c) 2023 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details. 

package io.synadia.flink.source;

import io.synadia.flink.common.ConnectionFactory;
import io.synadia.flink.common.NatsSubjectsConnection;
import io.synadia.flink.payload.PayloadDeserializer;
import io.synadia.flink.source.enumerator.NatsSourceEnumerator;
import io.synadia.flink.source.split.NatsSubjectCheckpointSerializer;
import io.synadia.flink.source.split.NatsSubjectSplit;
import io.synadia.flink.source.split.NatsSubjectSplitSerializer;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.*;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Flink Source to consumer data from one or more NATS subjects
 * @param <OutputT> the type of object to convert message payload data to
 */
public class NatsSource<OutputT> extends NatsSubjectsConnection implements
    Source<OutputT, NatsSubjectSplit, Collection<NatsSubjectSplit>>,
    ResultTypeQueryable<OutputT>
{
    private static final Logger LOG = LoggerFactory.getLogger(NatsSource.class);

    private final PayloadDeserializer<OutputT> payloadDeserializer;

    /**
     * Create a {@link NatsSourceBuilder} to allow the fluent construction of a new {@link NatsSource}.
     * @param <T> type of records being read
     * @return {@link NatsSourceBuilder}
     */
    public static <T> NatsSourceBuilder<T> builder() {
        return new NatsSourceBuilder<>();
    }

    NatsSource(List<String> subjects,
               PayloadDeserializer<OutputT> payloadDeserializer,
               ConnectionFactory connectionFactory) {
        super(subjects, connectionFactory);
        this.payloadDeserializer = payloadDeserializer;
    }

    @Override
    public Boundedness getBoundedness() {
        LOG.debug("Boundedness");
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<NatsSubjectSplit, Collection<NatsSubjectSplit>> createEnumerator(
        SplitEnumeratorContext<NatsSubjectSplit> enumContext) throws Exception
    {
        LOG.debug("createEnumerator");
        List<NatsSubjectSplit> list = new ArrayList<>();
        for (String subject : subjects) {
            list.add(new NatsSubjectSplit(subject));
        }
        return restoreEnumerator(enumContext, list);
    }

    @Override
    public SplitEnumerator<NatsSubjectSplit, Collection<NatsSubjectSplit>> restoreEnumerator(
        SplitEnumeratorContext<NatsSubjectSplit> enumContext,
        Collection<NatsSubjectSplit> checkpoint)
    {
        LOG.debug("restoreEnumerator");
        return new NatsSourceEnumerator(enumContext, checkpoint);
    }

    @Override
    public SimpleVersionedSerializer<NatsSubjectSplit> getSplitSerializer() {
        LOG.debug("getSplitSerializer");
        return new NatsSubjectSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<Collection<NatsSubjectSplit>> getEnumeratorCheckpointSerializer() {
        LOG.debug("getEnumeratorCheckpointSerializer");
        return new NatsSubjectCheckpointSerializer();
    }

    @Override
    public SourceReader<OutputT, NatsSubjectSplit> createReader(SourceReaderContext readerContext) throws Exception {
        LOG.debug("createReader");
        return new NatsSourceReader<>(connectionFactory, payloadDeserializer, readerContext);
    }

    /**
     * Get the payload deserializer registered for this source
     * @return the deserializer
     */
    public PayloadDeserializer<OutputT> getPayloadDeserializer() {
        LOG.debug("getPayloadDeserializer");
        return payloadDeserializer;
    }

    @Override
    public TypeInformation<OutputT> getProducedType() {
        return payloadDeserializer.getProducedType();
    }
}
