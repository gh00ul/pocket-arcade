class_name UiSpacer
extends UiView
## Compose's Spacer: empty room of a fixed size.


## Spacer(Modifier.height([param dp])).
static func h(dp: float) -> UiSpacer:
	var s := UiSpacer.new()
	s.height_dp = dp
	s.width_dp = 0.0
	return s


## Spacer(Modifier.width([param dp])).
static func w(dp: float) -> UiSpacer:
	var s := UiSpacer.new()
	s.width_dp = dp
	s.height_dp = 0.0
	return s
