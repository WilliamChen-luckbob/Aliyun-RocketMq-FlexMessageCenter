package com.wwstation.messagecenter.model.bo;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

/**
 * 用于暂存本地待消费者发送的消息数据
 * @author william
 * @description
 * @Date: 2021-03-05 14:53
 */
@Data
public class MessageBean {
    private String tag;
    private String key;
    private String dataJson;
    //可能为MQ送入的其它用于标记消息的属性，默认为空
    private JSONObject property;
}
