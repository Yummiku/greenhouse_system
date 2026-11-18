package greenhouse.message.entity;

import java.time.LocalDateTime;

public class PersonalMessage {
    private Long id;
    private String title;
    private String content;
    private String messageType;
    private Integer senderId;
    private Integer recipientId;
    private String sendStatus;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime withdrawnAt;
    private Integer recipientDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    //无参构造
    public PersonalMessage(){}

    //getter setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }
    public Integer getRecipientId() { return recipientId; }
    public void setRecipientId(Integer recipientId) { this.recipientId = recipientId; }
    public String getSendStatus() { return sendStatus; }
    public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public Integer getRecipientDeleted() { return recipientDeleted; }
    public void setRecipientDeleted(Integer recipientDeleted) { this.recipientDeleted = recipientDeleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
