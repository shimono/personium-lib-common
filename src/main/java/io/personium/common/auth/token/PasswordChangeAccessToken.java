/**
 * personium.io
 * Copyright 2019 FUJITSU LIMITED
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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for generating / parsing account password change access Token.
 */
public final class PasswordChangeAccessToken extends CellLocalAccessToken implements IAccessToken {

    static Logger log = LoggerFactory.getLogger(PasswordChangeAccessToken.class);

    /** Token prefix. */
    public static final String PREFIX_ACCESS = "AP~";

    static final int IDX_COUNT = 5;
    static final int IDX_ISSUED_AT = 0;
    static final int IDX_LIFESPAN = 1;
    static final int IDX_ISSUER = 4;
    static final int IDX_SUBJECT = 2;
    static final int IDX_SCHEMA = 3;

    /**
     * constructor.
     * Set an explicit validity period to generate a token.
     *
     * @param issuedAt Issued at(UNIXtime)
     * @param lifespan Lifespan(msec)
     * @param issuer Issuer
     * @param subject Subject
     * @param schema Schema
     */
    public PasswordChangeAccessToken(final long issuedAt, final long lifespan, final String issuer,
            final String subject, final String schema) {
        super(issuedAt, lifespan, issuer, subject, null, schema);
    }

    /**
     * constructor.
     * Set the validity period of the default value to generate a token.
     *
     * @param issuedAt Issued at(UNIXtime)
     * @param issuer Issuer
     * @param subject Subject
     * @param schema Schema
     */
    public PasswordChangeAccessToken(final long issuedAt, final String issuer, final String subject,
            final String schema) {
        this(issuedAt, ACCESS_TOKEN_EXPIRES_MILLISECS, issuer, subject, schema);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toTokenString() {
        StringBuilder ret = new StringBuilder(PREFIX_ACCESS);
        ret.append(this.doCreateTokenString(null));
        return ret.toString();
    }

    /**
     * Parses the token character string as Cell specified by issuer..
     *
     * @param token Token String
     * @param issuer Cell Root URL
     * @return token object
     * @throws AbstractOAuth2Token.TokenParseException Exception thrown when token parsing fails
     */
    public static PasswordChangeAccessToken parse(final String token, final String issuer)
            throws AbstractOAuth2Token.TokenParseException {
        if (!token.startsWith(PREFIX_ACCESS) || issuer == null) {
            throw AbstractOAuth2Token.PARSE_EXCEPTION;
        }
        String[] frag = LocalToken.doParse(token.substring(PREFIX_ACCESS.length()), issuer, IDX_COUNT);

        try {
            PasswordChangeAccessToken ret = new PasswordChangeAccessToken(
                    Long.valueOf(StringUtils.reverse(frag[IDX_ISSUED_AT])),
                    Long.valueOf(frag[IDX_LIFESPAN]),
                    frag[IDX_ISSUER],
                    frag[IDX_SUBJECT],
                    frag[IDX_SCHEMA]);
            return ret;
        } catch (Exception e) {
            throw AbstractOAuth2Token.PARSE_EXCEPTION;
        }
    }
}
