package com.wwstation.messagecenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wwstation.messagecenter.mapper.FailedMessageMapper;
import com.wwstation.messagecenter.model.po.FailedMessage;
import com.wwstation.messagecenter.service.MPFailedMessageService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author william
 * @since 2021-03-08
 */
@Service
public class MPFailedMessageServiceImpl extends ServiceImpl<FailedMessageMapper, FailedMessage> implements MPFailedMessageService {

}
