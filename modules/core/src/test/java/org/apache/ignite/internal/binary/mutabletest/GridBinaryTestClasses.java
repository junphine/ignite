/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.binary.mutabletest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import com.google.common.base.Throwables;
import org.apache.ignite.binary.BinaryMapFactory;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.binary.Binarylizable;
import org.apache.ignite.internal.util.lang.GridMapEntry;

/**
 *
 */
@SuppressWarnings({"PublicInnerClass", "PublicField"})
public class GridBinaryTestClasses {
    /**
     *
     */
    public static class TestObjectContainer {
        /** */
        public Object foo;

        /**
         *
         */
        public TestObjectContainer() {
            // No-op.
        }

        /**
         * @param foo Object.
         */
        public TestObjectContainer(Object foo) {
            this.foo = foo;
        }
    }

    /**
     *
     */
    public static class TestObjectOuter {
        /** */
        public TestObjectInner inner;

        /** */
        public String foo;

        /**
         *
         */
        public TestObjectOuter() {

        }

        /**
         * @param inner Inner object.
         */
        public TestObjectOuter(TestObjectInner inner) {
            this.inner = inner;
        }
    }

    /** */
    public static class TestObjectInner {
        /** */
        public Object foo;

        /** */
        public TestObjectOuter outer;
    }

    /** */
    public static class TestObjectArrayList {
        /** */
        public List<String> list = new ArrayList<>();
    }

    /**
     *
     */
    public static class TestObjectPlainBinary {
        /** */
        public BinaryObject plainBinary;

        /**
         *
         */
        public TestObjectPlainBinary() {
            // No-op.
        }

        /**
         * @param plainBinary Object.
         */
        public TestObjectPlainBinary(BinaryObject plainBinary) {
            this.plainBinary = plainBinary;
        }
    }

    /**
     *
     */
    public static class TestObjectAllTypes implements Serializable {
        /** */
        public Byte b_;

        /** */
        public Short s_;

        /** */
        public Integer i_;

        /** */
        public BigInteger bi_;

        /** */
        public Long l_;

        /** */
        public Float f_;

        /** */
        public Double d_;

        /** */
        public BigDecimal bd_;

        /** */
        public Character c_;

        /** */
        public Boolean z_;

        /** */
        public byte b;

        /** */
        public short s;

        /** */
        public int i;

        /** */
        public long l;

        /** */
        public float f;

        /** */
        public double d;

        /** */
        public char c;

        /** */
        public boolean z;

        /** */
        public String str;

        /** */
        public UUID uuid;

        /** */
        public Date date;

        /** */
        public Timestamp ts;

        /** */
        public byte[] bArr;

        /** */
        public short[] sArr;

        /** */
        public int[] iArr;

        /** */
        public long[] lArr;

        /** */
        public float[] fArr;

        /** */
        public double[] dArr;

        /** */
        public char[] cArr;

        /** */
        public boolean[] zArr;

        /** */
        public BigDecimal[] bdArr;

        /** */
        public String[] strArr;

        /** */
        public UUID[] uuidArr;

        /** */
        public Date[] dateArr;

        /** */
        public Timestamp[] tsArr;

        /** */
        public TestObjectEnum anEnum;

        /** */
        public TestObjectEnum[] enumArr;

        /** */
        public Map.Entry entry;

        /**
         * @return Array.
         */
        private byte[] serialize() {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

            try {
                ObjectOutput out = new ObjectOutputStream(byteOut);

                out.writeObject(this);

                out.close();
            }
            catch (IOException e) {
                Throwables.propagate(e);
            }

            return byteOut.toByteArray();
        }

        /**
         *
         */
        public void setDefaultData() {
            b_ = 11;
            s_ = 22;
            i_ = 33;
            bi_ = new BigInteger("33000000000000");
            l_ = 44L;
            f_ = 55f;
            d_ = 66d;
            bd_ = new BigDecimal("33000000000000.123456789");
            c_ = 'e';
            z_ = true;

            b = 1;
            s = 2;
            i = 3;
            l = 4;
            f = 5;
            d = 6;
            c = 7;
            z = true;

            str = "abc";
            uuid = new UUID(1, 1);
            date = new Date(1000000);
            ts = new Timestamp(100020003);

            bArr = new byte[] {1, 2, 3};
            sArr = new short[] {1, 2, 3};
            iArr = new int[] {1, 2, 3};
            lArr = new long[] {1, 2, 3};
            fArr = new float[] {1, 2, 3};
            dArr = new double[] {1, 2, 3};
            cArr = new char[] {1, 2, 3};
            zArr = new boolean[] {true, false};

            strArr = new String[] {"abc", "ab", "a"};
            uuidArr = new UUID[] {new UUID(1, 1), new UUID(2, 2)};
            bdArr = new BigDecimal[] {new BigDecimal(1000), BigDecimal.TEN};
            dateArr = new Date[] {new Date(1000000), new Date(200000)};
            tsArr = new Timestamp[] {new Timestamp(100020003), new Timestamp(200030004)};

            anEnum = TestObjectEnum.A;

            enumArr = new TestObjectEnum[] {TestObjectEnum.B};

            entry = new GridMapEntry<>(1, "a");
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (!(o instanceof TestObjectAllTypes))
                return false;

            TestObjectAllTypes allTypesObj = (TestObjectAllTypes)o;

            return b == allTypesObj.b
                && s == allTypesObj.s
                && i == allTypesObj.i
                && l == allTypesObj.l
                && Float.compare(f, allTypesObj.f) == 0
                && Double.compare(d, allTypesObj.d) == 0
                && c == allTypesObj.c
                && z == allTypesObj.z
                && Objects.equals(b_, allTypesObj.b_)
                && Objects.equals(s_, allTypesObj.s_)
                && Objects.equals(i_, allTypesObj.i_)
                && Objects.equals(bi_, allTypesObj.bi_)
                && Objects.equals(l_, allTypesObj.l_)
                && Objects.equals(f_, allTypesObj.f_)
                && Objects.equals(d_, allTypesObj.d_)
                && (bd_ == null ? allTypesObj.bd_ == null : bd_.compareTo(allTypesObj.bd_) == 0)
                && Objects.equals(c_, allTypesObj.c_)
                && Objects.equals(z_, allTypesObj.z_)
                && Objects.equals(str, allTypesObj.str)
                && Objects.equals(uuid, allTypesObj.uuid)
                && Objects.equals(date, allTypesObj.date)
                && Objects.equals(ts, allTypesObj.ts)
                && Arrays.equals(bArr, allTypesObj.bArr)
                && Arrays.equals(sArr, allTypesObj.sArr)
                && Arrays.equals(iArr, allTypesObj.iArr)
                && Arrays.equals(lArr, allTypesObj.lArr)
                && Arrays.equals(fArr, allTypesObj.fArr)
                && Arrays.equals(dArr, allTypesObj.dArr)
                && Arrays.equals(cArr, allTypesObj.cArr)
                && Arrays.equals(zArr, allTypesObj.zArr)
                && Arrays.equals(bdArr, allTypesObj.bdArr)
                && Arrays.equals(strArr, allTypesObj.strArr)
                && Arrays.equals(uuidArr, allTypesObj.uuidArr)
                && Arrays.equals(dateArr, allTypesObj.dateArr)
                && Arrays.equals(tsArr, allTypesObj.tsArr)
                && anEnum == allTypesObj.anEnum
                && Arrays.equals(enumArr, allTypesObj.enumArr)
                && Objects.equals(entry, allTypesObj.entry);
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            int res =
                Objects.hash(b_, s_, i_, bi_, l_, f_, d_, bd_, c_, z_, b, s, i, l, f, d, c, z, str, uuid, date, ts, anEnum, entry);

            res = 31 * res + Arrays.hashCode(bArr);
            res = 31 * res + Arrays.hashCode(sArr);
            res = 31 * res + Arrays.hashCode(iArr);
            res = 31 * res + Arrays.hashCode(lArr);
            res = 31 * res + Arrays.hashCode(fArr);
            res = 31 * res + Arrays.hashCode(dArr);
            res = 31 * res + Arrays.hashCode(cArr);
            res = 31 * res + Arrays.hashCode(zArr);
            res = 31 * res + Arrays.hashCode(bdArr);
            res = 31 * res + Arrays.hashCode(strArr);
            res = 31 * res + Arrays.hashCode(uuidArr);
            res = 31 * res + Arrays.hashCode(dateArr);
            res = 31 * res + Arrays.hashCode(tsArr);
            res = 31 * res + Arrays.hashCode(enumArr);

            return res;
        }
    }

    /**
     *
     */
    public enum TestObjectEnum {
        /** */
        A,

        /** */
        B,

        /** */
        C
    }

    /**
     *
     */
    public static class Address implements Serializable {
        /** SUID. */
        private static final long serialVersionUID = 0L;

        /** City. */
        public String city;

        /** Street. */
        public String street;

        /** Street number. */
        public int streetNumber;

        /** Flat number. */
        public int flatNumber;

        /**
         * Default constructor.
         */
        public Address() {
            // No-op.
        }

        /**
         * Constructor.
         *
         * @param city City.
         * @param street Street.
         * @param streetNumber Street number.
         * @param flatNumber Flat number.
         */
        public Address(String city, String street, int streetNumber, int flatNumber) {
            this.city = city;
            this.street = street;
            this.streetNumber = streetNumber;
            this.flatNumber = flatNumber;
        }

        /**  */
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Address address = (Address)o;
            return streetNumber == address.streetNumber && flatNumber == address.flatNumber
                    && Objects.equals(city, address.city) && Objects.equals(street, address.street);
        }

        /**  */
        @Override public int hashCode() {
            return Objects.hash(city, street, streetNumber, flatNumber);
        }
    }

    /**
     *
     */
    public static class Company implements Serializable {
        /** SUID. */
        private static final long serialVersionUID = 0L;

        /** ID. */
        public int id;

        /** Name. */
        public String name;

        /** Size. */
        public int size;

        /** Address. */
        public Address address;

        /** Occupation. */
        public String occupation;

        /**
         * Default constructor.
         */
        public Company() {
            // No-op.
        }

        /**
         * Constructor.
         *
         * @param id ID.
         * @param name Name.
         * @param size Size.
         * @param address Address.
         * @param occupation Occupation.
         */
        public Company(int id, String name, int size, Address address, String occupation) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.address = address;
            this.occupation = occupation;
        }
    }

    /**
     * Companies.
     */
    public static class Companies {
        /** Companies. */
        private List<Company> companies = new ArrayList<>();

        /**
         * @param idx Index.
         * @return Company.
         */
        public Company get(int idx) {
            return companies.get(idx);
        }

        /**
         * @param company Company.
         */
        public void add(Company company) {
            companies.add(company);
        }

        /**
         * @return Size.
         */
        public int size() {
            return companies.size();
        }
    }

    /**
     *
     */
    public static class Addresses implements Binarylizable {
        /** */
        private Map<String, Companies> companyByStreet = new TreeMap<>();

        /**
         * @param company Company.
         */
        public void addCompany(Company company) {
            Companies list = companyByStreet.get(company.address.street);

            if (list == null) {
                list = new Companies();

                companyByStreet.put(company.address.street, list);
            }

            list.add(company);
        }

        /**
         * @return map
         */
        public Map<String, Companies> getCompanyByStreet() {
            return companyByStreet;
        }

        /** {@inheritDoc} */
        @Override public void writeBinary(BinaryWriter writer) throws BinaryObjectException {
            writer.writeMap("companyByStreet", companyByStreet);
        }

        /** {@inheritDoc} */
        @Override public void readBinary(BinaryReader reader) throws BinaryObjectException {
            companyByStreet = reader.readMap("companyByStreet", new BinaryMapFactory<String, Companies>() {
                @Override public Map<String, Companies> create(int size) {
                    return new TreeMap<>();
                }
            });
        }
    }

    /**
     *
     */
    public static class CollectionsHolder {
        /** */
        public Collection<Object> firstCol;

        /** */
        public Collection<Object> secondCol;

        /** */
        public Object obj;
    }

    /**
     *
     */
    public static class MapsHolder {
        /** */
        public Map<Object, Object> firstMap;

        /** */
        public Map<Object, Object> secondMap;

        /** */
        public Object valObj;
    }
}
