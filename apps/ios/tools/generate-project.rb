# Run with bundle exec ruby tools/generate-project.rb when target membership changes.
require 'xcodeproj'
require 'fileutils'
Dir.chdir(File.expand_path('..', __dir__))
project = Xcodeproj::Project.new('Danmaku.xcodeproj')
project.root_object.known_regions = ['en', 'zh-TW', 'Base']
project.root_object.development_region = 'en'
core = project.new_target(:framework, 'DanmakuCore', :ios, '17.0')
app = project.new_target(:application, 'Danmaku', :ios, '17.0')
tests = project.new_target(:unit_test_bundle, 'DanmakuAppTests', :ios, '17.0')
core_group = project.main_group.new_group('Core', 'Sources/DanmakuCore')
Dir.glob('Sources/DanmakuCore/*.swift').sort.each { |file| core.source_build_phase.add_file_reference(core_group.new_file(File.basename(file))) }
app_group = project.main_group.new_group('App', 'App')
Dir.glob('App/*.swift').sort.each { |file| app.source_build_phase.add_file_reference(app_group.new_file(File.basename(file))) }
app_group.new_file('Info.plist')
resources = project.main_group.new_group('Resources', 'Resources')
app.resources_build_phase.add_file_reference(resources.new_file('Assets.xcassets'))
%w[Localizable.strings InfoPlist.strings].each do |name|
  variant = resources.new_variant_group(name)
  %w[en zh-TW].each do |locale|
    file = variant.new_file("#{locale}.lproj/#{name}")
    file.name = locale
  end
  app.resources_build_phase.add_file_reference(variant)
end
%w[LICENSE.txt THIRD_PARTY_NOTICES.txt VLCKit-COPYING.txt].each { |file| app.resources_build_phase.add_file_reference(resources.new_file(file)) }
test_group = project.main_group.new_group('AppTests', 'AppTests')
Dir.glob('AppTests/*.swift').sort.each { |file| tests.source_build_phase.add_file_reference(test_group.new_file(File.basename(file))) }
fixtures = project.main_group.new_group('PlaybackFixtures', '../../build/ios-fixtures')
%w[probe.mp4 probe.mkv probe.srt probe.ass].each { |file| tests.resources_build_phase.add_file_reference(fixtures.new_file(file)) }
app.add_dependency(core)
app.frameworks_build_phase.add_file_reference(core.product_reference)
embed = app.new_copy_files_build_phase('Embed Frameworks')
embed.dst_subfolder_spec = '10'
embed.add_file_reference(core.product_reference).settings = { 'ATTRIBUTES' => ['CodeSignOnCopy', 'RemoveHeadersOnCopy'] }
tests.add_dependency(app)
tests.add_dependency(core)
tests.frameworks_build_phase.add_file_reference(core.product_reference)
project.targets.each do |target|
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_VERSION' => '5.0', 'IPHONEOS_DEPLOYMENT_TARGET' => '17.0',
      'TARGETED_DEVICE_FAMILY' => '1,2', 'SUPPORTED_PLATFORMS' => 'iphoneos iphonesimulator',
      'CODE_SIGN_STYLE' => 'Automatic', 'MARKETING_VERSION' => '0.1.0', 'CURRENT_PROJECT_VERSION' => '1',
      'PRODUCT_BUNDLE_IDENTIFIER' => "app.danmaku.ios#{target == app ? '' : '.' + target.name.downcase}",
      'GENERATE_INFOPLIST_FILE' => 'YES', 'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
      'SUPPORTS_MACCATALYST' => 'NO', 'SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD' => 'NO'
    })
    if target == app
      config.build_settings['INFOPLIST_FILE'] = 'App/Info.plist'
      config.build_settings['GENERATE_INFOPLIST_FILE'] = 'NO'
      config.build_settings['ASSETCATALOG_COMPILER_APPICON_NAME'] = 'AppIcon'
    elsif target == core
      config.build_settings['DEFINES_MODULE'] = 'YES'
      config.build_settings['SKIP_INSTALL'] = 'YES'
    elsif target == tests
      config.build_settings['TEST_HOST'] = '$(BUILT_PRODUCTS_DIR)/Danmaku.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Danmaku'
      config.build_settings['BUNDLE_LOADER'] = '$(TEST_HOST)'
    end
  end
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.set_launch_target(app)
scheme.add_test_target(tests)
scheme.save_as('Danmaku.xcodeproj', 'Danmaku', true)
