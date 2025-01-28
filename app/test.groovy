


@Transactional
void updateMemberCountryCode(MemberContext memberContext, String countryCode) {
    var cacheKey = "${DellConstants.TENANT_KEY}_${memberContext.memberKey}"

    removeEntry("getMemberCountry", cacheKey)

    ElementData country = elementDataService.getByDataTypeAndCode(DellConstants.TENANT_KEY, DellElementDataType.COUNTRY.toString(), countryCode)
    if (!country) {
        throw new ValidationException('countrycode.not.found')
    }

    var memberDetail = memberContext.getCountryDetail()

    if (memberDetail && memberDetailCountryCode.code == countryCode) {
        throw new ValidationException('countrycode.not.changed')
    } else {
        memberDetail = new MemberDetail(
                tenantKey: memberContext.tenantKey,
                memberKey: memberContext.memberKey,
                detailKey: elementService.getByDataType(DellConstants.TENANT_KEY, DellElementDataType.COUNTRY.toString()).elementKey,
                valueKey: country.dataKey,
                detailType: DetailType.ELEMENT.toString(),
                createDate: DateUtil.dateAsLong,
                modifyDate: DateUtil.dateAsLong)
    }

    memberDetailService.save(memberDetail)


    ActivityRule pointClearRule = activityRuleService.getActiveByRuleCode(new Activity(tenantKey: DellConstants.TENANT_KEY, code: 'CLEARPOINTSREDEMP'))
    ActivityRule dellDollarClearRule = activityRuleService.getActiveByRuleCode(new Activity(tenantKey: DellConstants.TENANT_KEY, code: 'CLEARDELLDOLLARSSREDEMP'))

    BigDecimal totalPoints = BigDecimal.ZERO
    BigDecimal totalDellDollars = BigDecimal.ZERO
    memberContext.header.pointTypes.each {
        if (it.code == DellPointType.POINTS.toString()) {
            totalPoints += (it.points - it.redeem)
        }
        if (it.code == DellPointType.DELL_DOLLARS.toString()) {
            totalDellDollars += (it.points - it.redeem)
        }
    }

    if (totalPoints.compareTo(BigDecimal.ZERO) > 0) {
        activityService.addRedemption(memberContext, new Activity(
                memberKey: memberContext.memberKey,
                memberNumber: memberContext.memberNumber,
                tenantKey: DellConstants.TENANT_KEY,
                activityAmount: totalPoints,
                activityDate: memberContext.currentDate,
                code: pointClearRule.code
        ))
    }

    if (totalDellDollars.compareTo(BigDecimal.ZERO) > 0) {
        activityService.addRedemption(memberContext, new Activity(
                memberKey: memberContext.memberKey,
                memberNumber: memberContext.memberNumber,
                tenantKey: DellConstants.TENANT_KEY,
                activityAmount: totalDellDollars,
                activityDate: memberContext.currentDate,
                code: dellDollarClearRule.code
        ))
    }

    List<DellSavedSpecialOffer> specialOfferList = dellSavedSpecialOffersDao.find([memberkey: memberContext.memberKey] as HashMap)
    specialOfferList.each {
        it.status = 'X'
        dellSavedSpecialOffersDao.update(it)
    }

    List<DellCoupon> dellCoupons = dellCouponService.getActiveCouponsByMemberContext(memberContext)
    dellCoupons.each {
        it.status = 'X'
        dellCouponService.updateMemberCoupon(it)
    }


}