package searchengine.util;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import searchengine.config.SiteFromConfig;
import searchengine.model.Site;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SiteConverter implements Converter<List<SiteFromConfig>, List<Site>> {
    @Override
    public List<Site> convert(List<SiteFromConfig> source) {
        return source.stream().map(siteFromConfig -> {
                    Site site = new Site();
                    site.setUrl(siteFromConfig.getUrl());
                    site.setName(siteFromConfig.getName());
                    site.setStatus(Site.Status.INDEXING);
                    site.setLastError(null);
                    site.setStatusTime(LocalDateTime.now());
                    return site;
                })
                .collect(Collectors.toList());
    }
}
