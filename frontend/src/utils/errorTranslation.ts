/**
 * Error translation helper for converting server error codes and exceptions
 * into friendly, actionable Vietnamese user messages.
 */

const ERROR_CODE_MAP: Record<string, string> = {
  ROOM_NOT_FOUND: "Không tìm thấy phòng.",
  ROOM_FULL: "Phòng đã đủ số lượng người chơi.",
  AUTH_REQUIRED: "Phiên chơi không hợp lệ hoặc đã hết hạn.",
  SESSION_TOKEN_EXPIRED: "Phiên chơi đã hết hạn, vui lòng vào lại phòng.",
  INVALID_SESSION_TOKEN: "Phiên chơi không hợp lệ.",
  ROOM_SCOPE_MISMATCH: "Phiên chơi không khớp với phòng hiện tại.",
  NOT_HOST: "Chỉ chủ phòng mới có thể thực hiện thao tác này.",
  NOT_DRAWER: "Hiện tại chưa đến lượt bạn vẽ.",
  INVALID_PHASE: "Không thể thực hiện thao tác ở thời điểm này.",
  RATE_LIMITED: "Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.",
  KICKED: "Bạn đã bị chủ phòng mời khỏi phòng.",
  CANNOT_KICK_SELF: "Chủ phòng không thể tự mời chính mình ra.",
  INVALID_ROOM_CODE: "Mã phòng không hợp lệ (cần 4-32 ký tự).",
  INVALID_NICKNAME: "Tên người chơi không hợp lệ (1-32 ký tự).",
  ROOM_CREATE_FAILED: "Không thể tạo phòng, vui lòng thử lại.",
  ROOM_JOIN_FAILED: "Không thể vào phòng, vui lòng thử lại.",
  CANNOT_START_GAME: "Chưa đủ điều kiện để bắt đầu ván đấu.",
  START_GAME_FAILED:
    "Không thể bắt đầu ván đấu, vui lòng kiểm tra lại điều kiện.",
  SELECT_WORD_FAILED: "Không thể chọn từ khóa, vui lòng thử lại.",
  SUBMIT_GUESS_FAILED: "Không thể gửi câu trả lời, vui lòng thử lại.",
  RECOVERY_NOT_AVAILABLE: "Không thể khôi phục bảng vẽ của vòng này.",
  DRAWING_NOT_ACTIVE: "Lượt vẽ chưa bắt đầu hoặc đã kết thúc.",
  GAME_NOT_ACTIVE: "Ván đấu hiện không hoạt động.",
  PLAYER_NOT_IN_ROOM: "Bạn không còn trong phòng này.",
  INVALID_SESSION: "Phiên kết nối không hợp lệ.",
};

export function translateError(error: any): string {
  if (!error) return "Đã có lỗi xảy ra, vui lòng thử lại.";

  // Check if error is a string
  if (typeof error === "string") {
    const trimmed = error.trim();
    if (ERROR_CODE_MAP[trimmed]) {
      return ERROR_CODE_MAP[trimmed];
    }
    // Check if error contains any known code
    for (const [code, translation] of Object.entries(ERROR_CODE_MAP)) {
      if (trimmed.includes(code)) {
        return translation;
      }
    }
    // Friendly fallback for common patterns
    if (/room.*not found|not found.*room/i.test(trimmed))
      return "Không tìm thấy phòng.";
    if (/not found/i.test(trimmed)) return "Không tìm thấy dữ liệu yêu cầu.";
    if (/full/i.test(trimmed)) return "Phòng đã đủ số lượng người chơi.";
    if (/timeout/i.test(trimmed))
      return "Yêu cầu đã quá thời gian chờ, vui lòng thử lại.";
    if (/connection|network/i.test(trimmed))
      return "Lỗi kết nối mạng, đang thử lại...";
    if (/rate limit/i.test(trimmed))
      return "Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.";
    return trimmed;
  }

  // Check for wsError code
  const code = error?.code || error?.wsError?.code || error?.errorCode;
  if (code && ERROR_CODE_MAP[code]) {
    return ERROR_CODE_MAP[code];
  }

  const message = error?.message || error?.error?.message;
  if (message && typeof message === "string") {
    return translateError(message);
  }

  return "Đã xảy ra lỗi máy chủ, vui lòng thử lại.";
}
