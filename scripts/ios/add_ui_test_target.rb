# Adds the iosAppUITests UI-test target to iosApp.xcodeproj and to the shared iosApp scheme. Idempotent.
#
#   gem install xcodeproj      # once (CocoaPods installs it as well)
#   ruby scripts/ios/add_ui_test_target.rb
#
# Written on Windows and NOT RUN there: check `git diff iosApp/` after running it and open the project once in Xcode.
require 'xcodeproj'

root = File.expand_path('../..', __dir__)
project_path = File.join(root, 'iosApp', 'iosApp.xcodeproj')
target_name = 'iosAppUITests'
sources = ['AppSmokeUITests.swift']

project = Xcodeproj::Project.open(project_path)
app = project.targets.find { |t| t.name == 'iosApp' } or abort('target "iosApp" not found')

if project.targets.any? { |t| t.name == target_name }
  puts "#{target_name} already exists, nothing to do"
  exit 0
end

target = project.new_target(:ui_test_bundle, target_name, :ios, '15.0')
target.add_dependency(app)

group = project.main_group.new_group(target_name, target_name)
target.add_file_references(sources.map { |file| group.new_file(file) })

app_bundle_id = app.build_configurations.first.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] || 'com.habitsheet.app'
target.build_configurations.each do |config|
  settings = config.build_settings
  settings['TEST_TARGET_NAME'] = 'iosApp'
  settings['PRODUCT_BUNDLE_IDENTIFIER'] = "#{app_bundle_id}.uitests"
  settings['GENERATE_INFOPLIST_FILE'] = 'YES'
  settings['SWIFT_VERSION'] = '5.0'
  settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'
  settings['CODE_SIGN_STYLE'] = 'Automatic'
end

project.save

scheme_file = File.join(Xcodeproj::XCScheme.shared_data_dir(project_path), 'iosApp.xcscheme')
scheme = Xcodeproj::XCScheme.new(scheme_file)
scheme.add_test_target(target)
scheme.save!

puts "Added #{target_name} to #{project_path} and to the iosApp scheme"
