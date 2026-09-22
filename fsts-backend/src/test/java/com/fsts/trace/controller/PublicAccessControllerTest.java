package com.fsts.trace.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 扫码溯源配套接口测试：契约（路径 / 响应结构）与内网守卫规则。
 */
class PublicAccessControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicAccessController()).build();

    @Test
    void returnsLanHostsForPrivateCaller() throws Exception {
        mockMvc.perform(from("192.168.1.20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void hidesLanHostsFromPublicCaller() throws Exception {
        mockMvc.perform(from("203.0.113.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private MockHttpServletRequestBuilder from(String remoteAddr) {
        return get("/api/public/access-hosts").with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        });
    }
}
