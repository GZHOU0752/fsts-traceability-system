package com.fsts.trace.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 溯源链环节凭证（{@code certificates} 元素）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CertificateVO {

    private String name;

    private String no;
}
