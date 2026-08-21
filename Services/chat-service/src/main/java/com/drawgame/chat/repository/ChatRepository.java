package com.drawgame.chat.repository;

import com.drawgame.chat.domain.ChatMessage;

import java.util.List;

public interface ChatRepository {

    ChatMessage append(ChatMessage message);

    List<ChatMessage> findRecent(String roomId, int limit);
}
