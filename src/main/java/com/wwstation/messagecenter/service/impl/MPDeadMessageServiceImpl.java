package com.wwstation.messagecenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wwstation.messagecenter.mapper.DeadMessageMapper;
import com.wwstation.messagecenter.model.po.DeadMessage;
import com.wwstation.messagecenter.service.MPDeadMessageService;
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
public class MPDeadMessageServiceImpl extends ServiceImpl<DeadMessageMapper, DeadMessage> implements MPDeadMessageService {

}
