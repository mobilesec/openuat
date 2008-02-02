/* Copyright Rene Mayrhofer
 * File created 2008-01-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import org.apache.log4j.Logger;

/** This is an abstract class that implements the basics of all protocols
 * based on/belonging to the MANA IV family of multi-channel authentication
 * protocols as specified in 
 * [Sven Laur and Kaisa Nyberg: Efﬁcient Mutual Data Authentication Using 
 * Manually Authenticated Strings: Extended Version"
 *
 * 
 * Mana IV:
 * 1. Alice computes (c, d) ← Com_pk (ka ) for random ka ← Ka and sends (ma , c) to Bob.
2. Bob chooses random kb ← Kb and sends (mb , kb ) to Alice.
3. Alice sends d to Bob, who computes ka ← Open_pk (c, d) and halts if ka = ⊥.
   Both parties compute a test value oob = h(ma ||mb , ka , kb ) from the received messages.
4. Both parties accept (ma , mb ) iff the local -bit test values ooba and oobb coincide.
 *
 * Speciﬁcation: h is a keyed hash function with sub-keys ka , kb where Ka is a message space
of commitment scheme. The hash function h and the public parameters pk of the commitment
scheme are ﬁxed and distributed by a trusted authority.
 * 
 * MA-DH:
 * 1. Alice computes (c, d) ← Com_pk (ka ) for ka = g^a , a ← Z_q and sends (ida , c) to Bob.
2. Bob computes kb = g^b for random b ← Z_q and sends (idb , kb ) to Alice.
3. Alice sends d to Bob, who computes ka ← Open_pk (c, d) and halts if ka = ⊥.
   Both parties compute sid = (ida , idb ) and oob = h(sid, ka , kb ) from the received messages.
4. Both parties accept key = (g^a )^b = (g^b )^a iff the -bit test values ooba and oobb coincide.
 * 
 * Speciﬁcation: h is a keyed hash function with sub-keys ka , kb ∈ G where G = g is a q
element Decisional Difﬁe-Hellman group; G is a message space of commitment scheme. Public
parameters pk and G are ﬁxed and distributed by a trusted authority. Device identiﬁers ida and
idb must be unique in time, for example, a device address followed by a session counter.
 * 
 * 
 *     In reality, a cryptographic hash functions like SHA-1 are used instead of commit-
ments, as such constructions are hundred times faster and there are no setup assump-
tions. Let H be a collision resistant hash function. Then the hash commitment is com-
puted as (c, d) ← Com(x, r) with c = H(x||r) and d = (x, r) or, as in HMAC,
c = H(r ⊕ opad||H(r ⊕ ipad||x)) with d = r (See [BT06, p. 13] as an exam-
ple). Both constructions are a priori not hiding. We would like to have a provably
secure construction. In theory, we could use one-wayness of H and deﬁne commit-
ment with hard-core bits but this leads to large commitments. Instead, we use Bellare-
Rogaway random oracle design principle to heuristically argue that a hash commitment
based on the OAEP padding is a better alternative. Recall that the OAEP padding is
c = H(s, t), s = (x||0k0 ) ⊕ g(r), t = r ⊕ f (s). The corresponding commitment c
along with d = r is provably hiding and binding if g is pseudorandom, f is random
oracle, and H is collision resistant. A priori SHA-1 and SHA-512 are not known to
be non-malleable, as it has never been a design goal. On the other hand, the security
proof of OAEP [FOPS01] shows CCA2 security (non-malleability) provided that H is
a partial-domain one-way permutation. More speciﬁcally, it should be infeasible to ﬁnd
                                                                                        80
s given h(s, t), s ← M1 , t ← M2 . The partial one-wayness follows for r, t ∈ {0, 1}
                                    160 −20
if we assume that H is at least (2 , 2 )-collision resistant as we can enumerate all
possible t values to get a collision. The other assumption that h is a permutation is im-
portant in the proof. Therefore, we can only conjecture that the proof can be generalised
and the OAEP padding provides a non-malleable commitment scheme.

Practical implementations [ZJC06,WUS06] of the MA–DH protocol use c = H(g a )
and such a relaxed security proof would bridge the gap between theory and practice.

 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ManaIV extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.ManaIV" /*ManaIV.class*/);

	/**
	 * 
	 * @param combinedMaDH Set to true if the protocol should run an instance
	 *        of the MA-DH protocol that combines Diffie-Hellman key exchange
	 *        with verification over an out-of-band channel (this is probably
	 *        what you want if no specific protocol design is used). 
	 *        Set to false if an instance of the Mana IV protocol should run
	 *        for using a key agreement different from Diffie-Hellman.
	 */
	public ManaIV(boolean combinedMaDH) {
		
	}
}
