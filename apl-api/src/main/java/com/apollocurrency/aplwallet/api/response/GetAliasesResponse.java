package com.apollocurrency.aplwallet.api.response;

import com.apollocurrency.aplwallet.api.dto.AliasDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetAliasesResponse extends ResponseBase{
    public AliasDTO[] aliases;
}
