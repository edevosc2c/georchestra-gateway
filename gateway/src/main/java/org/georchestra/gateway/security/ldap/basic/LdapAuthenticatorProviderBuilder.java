/*
 * Copyright (C) 2022 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.ldap.basic;

import static java.util.Objects.requireNonNull;

import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

import lombok.Setter;
import lombok.experimental.Accessors;

/**
 */
@Accessors(chain = true, fluent = true)
public class LdapAuthenticatorProviderBuilder {

    private @Setter String url;
    private @Setter String baseDn;

    private @Setter String userSearchBase;
    private @Setter String userSearchFilter;

    private @Setter String rolesSearchBase;
    private @Setter String rolesSearchFilter;

    private @Setter String adminDn;
    private @Setter String adminPassword;

    public LdapAuthenticationProvider build() {
        requireNonNull(url, "url is not set");
        requireNonNull(baseDn, "baseDn is not set");
        requireNonNull(userSearchBase, "userSearchBase is not set");
        requireNonNull(userSearchFilter, "userSearchFilter is not set");
        requireNonNull(rolesSearchBase, "rolesSearchBase is not set");
        requireNonNull(rolesSearchFilter, "rolesSearchFilter is not set");

        final BaseLdapPathContextSource source = contextSource();
        final BindAuthenticator authenticator = ldapAuthenticator(source);
        final DefaultLdapAuthoritiesPopulator rolesPopulator = ldapAuthoritiesPopulator(source);

        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(authenticator, rolesPopulator);

        final GrantedAuthoritiesMapper rolesMapper = ldapAuthoritiesMapper();
        provider.setAuthoritiesMapper(rolesMapper);
        return provider;
    }

    private BindAuthenticator ldapAuthenticator(BaseLdapPathContextSource contextSource) {
        FilterBasedLdapUserSearch search = new FilterBasedLdapUserSearch(userSearchBase, userSearchFilter,
                contextSource);

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(search);
        authenticator.afterPropertiesSet();
        return authenticator;
    }

    private BaseLdapPathContextSource contextSource() {
        LdapContextSource context = new LdapContextSource();
        context.setUrl(url);
        context.setBase(baseDn);
        if (adminDn != null) {
            context.setUserDn(adminDn);
            context.setPassword(adminPassword);
        }
        context.afterPropertiesSet();
        return context;
    }

    private GrantedAuthoritiesMapper ldapAuthoritiesMapper() {
        return new SimpleAuthorityMapper();
    }

    private DefaultLdapAuthoritiesPopulator ldapAuthoritiesPopulator(BaseLdapPathContextSource contextSource) {
        DefaultLdapAuthoritiesPopulator authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource,
                rolesSearchBase);
        authoritiesPopulator.setGroupSearchFilter(rolesSearchFilter);
        return authoritiesPopulator;
    }
}