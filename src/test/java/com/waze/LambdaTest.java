package com.waze;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.waze.lambda.LambdaSNSEndpoints;
import org.assertj.core.util.Maps;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LambdaTest {
//    @ClassRule
//    public static final DropwizardAppRule<WazeConfig> RULE =
//            new DropwizardAppRule<>(WazeApp.class, ResourceHelpers.resourceFilePath("conf.yml"));

    @Test
    public void LambdaTest() {
        SNSEvent snsEvent = new SNSEvent();
        SNSEvent.SNSRecord snsRecord = new SNSEvent.SNSRecord();

        SNSEvent.SNS sns = new SNSEvent.SNS();
        sns.setMessage("1712 Kilbourne Pl NW, Washington, DC 20010 to 2150 K St NW, Washington, DC 20427");
        SNSEvent.MessageAttribute messageAttribute = new SNSEvent.MessageAttribute();
        messageAttribute.setValue("443-676-8386");
        Map<String, SNSEvent.MessageAttribute> messageAttributeMap = Maps.newHashMap();
        messageAttributeMap.put("originationNumber", messageAttribute);
        sns.setMessageAttributes(messageAttributeMap);

        snsRecord.setSns(sns);
        snsEvent.setRecords(Collections.singletonList(snsRecord));

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);
        when(context.getLogger()).thenReturn(logger);

        LambdaSNSEndpoints lambdaSNSEndpoints = new LambdaSNSEndpoints();
        lambdaSNSEndpoints.handleRequest(snsEvent, context);
    }
}