/**
 * Personium
 * Copyright 2014-2019 Personium Project Authors
 * - FUJITSU LIMITED
 * - (Add Authors here)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.personium.common.auth.token;

import java.net.MalformedURLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for creating and parsing Visitor Local Access Token.
 */
public class VisitorLocalAccessToken extends AbstractLocalAccessToken implements IAccessToken, IExtRoleContainingToken {

    /**
     * Logger.
     */
    static Logger log = LoggerFactory.getLogger(VisitorLocalAccessToken.class);

    /**
     * Token Prefix.
     */
    public static final String PREFIX_ACCESS = "AV~";

    /**
     * Token Type String.
     */
    @Override
    int getType() {
        return AbstractLocalToken.Type.AccessToken.VISITOR_LOCAL;
    }


    public VisitorLocalAccessToken() {
    };

    /**
     * Constructor for generating code.
     * @param issuedAt issuedAt
     * @param lifespan lifespan
     * @param issuer issuer
     * @param subject subject
     * @param roleList roleList
     * @param schema schema
     * @param scope scope
     */
    public VisitorLocalAccessToken(final long issuedAt,
            final long lifespan,
            final String issuer,
            final String subject,
            final List<Role> roleList,
            final String schema,
            final String[] scope) {
        super(issuedAt, lifespan, issuer, subject, schema, scope);
        if (roleList != null) {
            this.roleList = roleList;
        }
    }


    @Override
    public String toTokenString() {
        StringBuilder ret = new StringBuilder(PREFIX_ACCESS);
        ret.append(this.doCreateTokenString(new String[] {this.makeRolesString()}));
        return ret.toString();
    }


    /**
     * トークン文字列をissuerで指定されたCellとしてパースする.
     * @param token Token String
     * @param issuer Cell Root URL
     * @return パースされたCellLocalTokenオブジェクト
     * @throws AbstractOAuth2Token.TokenParseException トークンのパースに失敗したとき投げられる例外
     */
    public static VisitorLocalAccessToken parse(final String token, final String issuer)
            throws AbstractOAuth2Token.TokenParseException {
        if (!token.startsWith(PREFIX_ACCESS) || issuer == null) {
            throw AbstractOAuth2Token.PARSE_EXCEPTION;
        }
        VisitorLocalAccessToken ret = new VisitorLocalAccessToken();
        String[] ext = ret.populate(token.substring(PREFIX_ACCESS.length()), issuer, 1);
        try {
            ret.roleList = AbstractOAuth2Token.parseRolesString(ext[0]);
            return ret;
        } catch (MalformedURLException e) {
            throw new TokenParseException(e.getMessage(), e);
        }
    }


    @Override
    public String getTarget() {
        return null;
    }

    @Override
    public String getId() {
        return this.subject + ":" + this.issuedAt;
    }

    @Override
    public String getExtCellUrl() {
        return this.issuer;
    }

    @Override
    public List<Role> getRoleList() {
        return this.roleList;
    }

}
