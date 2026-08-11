package com.xzcpc.mp.controller;

import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.dto.TransferReturnReq;
import com.xzcpc.mp.service.TransferReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mp/transfers")
@RequiredArgsConstructor
public class MpTransferReturnController {

    private final TransferReturnService returnService;

    /**
     * 还货/还钱操作
     */
    @PostMapping("/{transferId}/return")
    public R<Void> doReturn(@PathVariable Long transferId, @RequestBody TransferReturnReq req) {
        var user = UserContextHolder.get();
        returnService.doReturn(transferId, user.getStoreId(), user.getEmployeeName(), req);
        return R.ok();
    }

    /**
     * 调出门店确认还货完成
     */
    @PostMapping("/{transferId}/return-confirm")
    public R<Void> confirmReturn(@PathVariable Long transferId) {
        var user = UserContextHolder.get();
        returnService.confirmReturn(transferId, user.getStoreId());
        return R.ok();
    }
}
