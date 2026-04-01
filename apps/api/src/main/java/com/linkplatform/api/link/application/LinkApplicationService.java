package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;

public interface LinkApplicationService {

    Link createLink(CreateLinkCommand command);

    LinkDetails updateLink(String slug, String originalUrl);

    void deleteLink(String slug);

    Link resolveLink(String slug);

    LinkDetails getLink(String slug);

    List<LinkDetails> listRecentLinks(int limit);
}
