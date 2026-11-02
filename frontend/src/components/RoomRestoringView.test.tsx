import { describe, it, expect, vi } from "vitest";
import { renderToString } from "react-dom/server";
import { RoomRestoringView } from "./RoomRestoringView";

describe("RoomRestoringView", () => {
  it("renders restoring spinner and room ID when restoring", () => {
    const html = renderToString(
      <RoomRestoringView
        roomId="TEST12"
        isRestoring={true}
        error={null}
        onGoHome={vi.fn()}
      />
    );

    expect(html).toContain("Đang kết nối lại phòng...");
    expect(html).toContain("TEST12");
    expect(html).toContain("Đang đồng bộ phiên chơi và bàn vẽ của bạn...");
    expect(html).toContain("Hủy &amp; Quay về trang chủ");
  });

  it("renders error state when an error message is provided", () => {
    const html = renderToString(
      <RoomRestoringView
        roomId="TEST12"
        isRestoring={false}
        error="Phòng không tồn tại hoặc đã kết thúc"
        onGoHome={vi.fn()}
      />
    );

    expect(html).toContain("Không thể kết nối lại phòng");
    expect(html).toContain("TEST12");
    expect(html).toContain("Phòng không tồn tại hoặc đã kết thúc");
    expect(html).toContain("Quay về trang chủ");
  });
});
