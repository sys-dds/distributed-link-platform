package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;

public interface LinkApplicationService {

    Link createLink(CreateLinkCommand command);

    Link resolveLink(String slug);
}
