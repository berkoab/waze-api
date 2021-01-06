package com.waze.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.waze.domain.WazeRouteDirection;
import com.waze.domain.WazeRoutePart;
import com.waze.domain.WazeRouteWithDirectionsResponse;
import com.waze.service.WazeRouteService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pinpoint.PinpointClient;
import software.amazon.awssdk.services.pinpoint.model.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
;


public class LambdaSNSEndpoints implements RequestHandler<SNSEvent, Object> {

    private final WazeRouteService wazeRouteService;
    private Context context;

    private static Map<String, String> INSTRUCTIONS = Maps.newHashMap();

    static {
        INSTRUCTIONS.put("KEEP_RIGHT", "Keep Right on ");
        INSTRUCTIONS.put("KEEP_LEFT", "Keep Left on ");
        INSTRUCTIONS.put("TURN_RIGHT", "Turn Right on ");
        INSTRUCTIONS.put("TURN_LEFT", "Turn Left on ");
        INSTRUCTIONS.put("ROUNDABOUT_ENTER", "Enter Roundabout into ");
        INSTRUCTIONS.put("ROUNDABOUT_EXIT", "Exit Roundabout onto ");
        INSTRUCTIONS.put("APPROACHING_DESTINATION", "Arrive at destination");
        INSTRUCTIONS.put("CONTINUE", "Continue on ");
        INSTRUCTIONS.put("UTURN", "Make a UTurn on ");
    }

    private final PinpointClient pinpointClient;
    private final static String appId = System.getenv().getOrDefault("appId", "8e0de9fc55404a8da7890f8b3dadfffc");
    private final static String originationNumber = System.getenv().getOrDefault("originationNumber", "+12074079137");
    private final static String keyword = System.getenv().getOrDefault("keyword", "keyword_835649543050");

    public LambdaSNSEndpoints() {
        this.wazeRouteService = new WazeRouteService();
        pinpointClient = PinpointClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Override
    public Object handleRequest(SNSEvent request, Context context) {
        this.context = context;
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);

        SNSEvent.SNS sns = request.getRecords().get(0).getSNS();
        String messageBodyJson = sns.getMessage();

        MessageModel messageModel = new Gson().fromJson(messageBodyJson, MessageModel.class);
        // Parse the city and phone number
        String originationNumber = messageModel.getOriginationNumber();
        String message = messageModel.getMessageBody();

        NumberValidateResponse numberValidateResponse = validatePhoneNumber(originationNumber);
        if(numberValidateResponse.phoneTypeCode() != 0) {
            context.getLogger().log("Couldn't validate phone number: " + originationNumber);
            return null;
        } else {
            originationNumber = numberValidateResponse.cleansedPhoneNumberE164();
        }
//        context.getLogger().log("Phone Number: " + phoneNumber);
//        context.getLogger().log("Message: " + message);
        String[] parts = message.split(" to ");
        if(parts.length < 2) {
            String newMessage = "You must include an origination address, the word \" to \" and the destination address";
            sendSMSMessage(newMessage, originationNumber);
            return null;
        }
        String start = parts[0];
        String end = parts[1];
        WazeRouteWithDirectionsResponse directions = wazeRouteService.getRouteWithParts(start, end);
        StringBuilder responseBuffer = new StringBuilder();

        int shortestLength = 1000000;
        WazeRouteDirection fastestRoute = null;

        for(WazeRouteDirection direction: directions.getRoutesWithDirections()) {
            int length = direction.getRouteDurationInMinutes();
            if(length < shortestLength) {
                shortestLength = length;
                fastestRoute = direction;
            }
        }
        String instruction = null;
        boolean getNextStreet = false;
        boolean writeMiles = false;
        int partsLength = 0;
        String previousStreet = null;
//        System.out.println(new Gson().toJson(fastestRoute));
        for(WazeRoutePart part:fastestRoute.getRouteParts()) {
            if(getNextStreet) {
                responseBuffer.append(INSTRUCTIONS.get(instruction));
                if(!part.getStreetName().equals("null")) {
                    responseBuffer.append(part.getStreetName());
                } else {
                    responseBuffer.append(previousStreet);
                }
                writeMiles = true;
                getNextStreet = false;
            }
            if(part.getInstruction() != null && !part.getInstruction().equals("NONE")) {
                if(writeMiles) {
                    writeMiles(responseBuffer, partsLength);
                }
                responseBuffer.append("\n");
                partsLength = 0;

                instruction = part.getInstruction();
                getNextStreet = true;
                previousStreet = part.getStreetName();
            }
            if(part.getInstruction() != null && part.getInstruction().equals("APPROACHING_DESTINATION")) {
                responseBuffer.append(INSTRUCTIONS.get(instruction));
                writeMiles(responseBuffer, part.getLengthOfPartInMeters());
            }
            partsLength += part.getLengthOfPartInMeters();
        }
        responseBuffer.append("\n");
        responseBuffer.append("Total time: ");
        responseBuffer.append(fastestRoute.getRouteDurationInMinutes());
        responseBuffer.append(" minutes.");
        responseBuffer.append("\n");
        responseBuffer.append("Total length: ");
        responseBuffer.append(kmToMiles(fastestRoute.getRouteLengthKM()));
        responseBuffer.append(" miles.");

//        System.out.println(responseBuffer.toString());

        sendSMSMessage(responseBuffer.toString(), originationNumber);

        context.getLogger().log("Invocation completed: " + timeStamp);
        return null;
    }

    private NumberValidateResponse validatePhoneNumber(String phoneNumber) {
        if(phoneNumber.length() == 10) {
            phoneNumber = "+1" + phoneNumber;
        }
        PhoneNumberValidateRequest request = PhoneNumberValidateRequest
                .builder()
                .numberValidateRequest(NumberValidateRequest
                        .builder()
                        .isoCountryCode("US")
                        .phoneNumber(phoneNumber)
                        .build())
                .build();
        PhoneNumberValidateResponse response = pinpointClient.phoneNumberValidate(request);

        return response.numberValidateResponse();
    }

    private double kmToMiles(double km) {
        double miles = km/1.609344;
        return Math.round(miles * 100.0) / 100.0;
    }

    private void writeMiles(StringBuilder responseBuffer, int partsLength) {
        responseBuffer.append(" - ");
        double milesPart= partsLength*0.000621371192;
        double miles = kmToMiles(Math.round(milesPart * 100.0) / 100.0);
        responseBuffer.append(miles);
        responseBuffer.append("m");
    }

    public void sendSMSMessage(String message, String destinationNumber) {
        String messageType = "TRANSACTIONAL";
        try {

            Map<String, AddressConfiguration> addressMap =
                    new HashMap<>();

            AddressConfiguration addConfig = AddressConfiguration.builder()
                    .channelType(ChannelType.SMS)
                    .build();

            addressMap.put(destinationNumber, addConfig);

            SMSMessage smsMessage = SMSMessage.builder()
                    .body(message)
                    .messageType(messageType)
                    .originationNumber(originationNumber)
                    .keyword(keyword)
                    .build();

            // Create a DirectMessageConfiguration object
            DirectMessageConfiguration direct = DirectMessageConfiguration.builder()
                    .smsMessage(smsMessage)
                    .build();

            MessageRequest msgReq = MessageRequest.builder()
                    .addresses(addressMap)
                    .messageConfiguration(direct)
                    .build();

            // create a  SendMessagesRequest object
            SendMessagesRequest request = SendMessagesRequest.builder()
                    .applicationId(appId)
                    .messageRequest(msgReq)
                    .build();

            pinpointClient.sendMessages(request);

//            MessageResponse msg1 = response.messageResponse();

        } catch (PinpointException e) {
            context.getLogger().log(e.awsErrorDetails().errorMessage());
        }
    }
}
