import { describe, it, expect } from "vitest";
import { translateError } from "./errorTranslation";

describe("translateError", () => {
  it("translates known error codes to friendly Vietnamese messages", () => {
    expect(translateError("ROOM_NOT_FOUND")).toBe("Không tìm thấy phòng.");
    expect(translateError("ROOM_FULL")).toBe(
      "Phòng đã đủ số lượng người chơi.",
    );
    expect(translateError("AUTH_REQUIRED")).toBe(
      "Phiên chơi không hợp lệ hoặc đã hết hạn.",
    );
    expect(translateError("NOT_HOST")).toBe(
      "Chỉ chủ phòng mới có thể thực hiện thao tác này.",
    );
    expect(translateError("NOT_DRAWER")).toBe("Hiện tại chưa đến lượt bạn vẽ.");
    expect(translateError("RATE_LIMITED")).toBe(
      "Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.",
    );
    expect(translateError("KICKED")).toBe(
      "Bạn đã bị chủ phòng mời khỏi phòng.",
    );
  });

  it("handles object errors with code or message", () => {
    expect(translateError({ code: "ROOM_NOT_FOUND" })).toBe(
      "Không tìm thấy phòng.",
    );
    expect(translateError({ wsError: { code: "RATE_LIMITED" } })).toBe(
      "Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.",
    );
    expect(translateError(new Error("Room not found for code ABC"))).toBe(
      "Không tìm thấy phòng.",
    );
  });

  it("safely handles empty or unknown errors without leaking stack traces", () => {
    expect(translateError(null)).toBe("Đã có lỗi xảy ra, vui lòng thử lại.");
    expect(translateError(undefined)).toBe(
      "Đã có lỗi xảy ra, vui lòng thử lại.",
    );
    expect(translateError({ error: {} })).toBe(
      "Đã xảy ra lỗi máy chủ, vui lòng thử lại.",
    );
  });
});
