package com.waze.lambda;

public class MessageModel {
    String originationNumber;
    String destinationNumber;
    String messageKeyword;
    String messageBody;
    String inboundMessageId;
    String previousPublishedMessageId;

    public String getOriginationNumber() {
        return originationNumber;
    }

    public void setOriginationNumber(String originationNumber) {
        this.originationNumber = originationNumber;
    }

    public String getDestinationNumber() {
        return destinationNumber;
    }

    public void setDestinationNumber(String destinationNumber) {
        this.destinationNumber = destinationNumber;
    }

    public String getMessageKeyword() {
        return messageKeyword;
    }

    public void setMessageKeyword(String messageKeyword) {
        this.messageKeyword = messageKeyword;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public String getInboundMessageId() {
        return inboundMessageId;
    }

    public void setInboundMessageId(String inboundMessageId) {
        this.inboundMessageId = inboundMessageId;
    }

    public String getPreviousPublishedMessageId() {
        return previousPublishedMessageId;
    }

    public void setPreviousPublishedMessageId(String previousPublishedMessageId) {
        this.previousPublishedMessageId = previousPublishedMessageId;
    }
}
