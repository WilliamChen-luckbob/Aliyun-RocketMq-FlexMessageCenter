package com.wwstation.messagecenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wwstation.messagecenter.mapper.ConsumerConfigMapper;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 消费者配置表 服务实现类
 * </p>
 *
 * @author william
 * @since 2021-03-08
 */
@Service
public class MPConsumerConfigServiceImpl extends ServiceImpl<ConsumerConfigMapper, ConsumerConfig> implements MPConsumerConfigService {

}
