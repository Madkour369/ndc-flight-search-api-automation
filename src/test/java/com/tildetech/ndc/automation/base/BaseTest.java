package com.tildetech.ndc.automation.base;

import com.tildetech.ndc.automation.specs.RequestSpecFactory;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.BeforeClass;

public abstract class BaseTest {

    protected RequestSpecification requestSpec;

    @BeforeClass(alwaysRun = true)
    public void setUpRequestSpec() {
        requestSpec = RequestSpecFactory.defaultSpec();
    }
}
